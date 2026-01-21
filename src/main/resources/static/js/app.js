// BatchOps Admin UI helpers (no framework)
(() => {
  'use strict';

  const qs = (sel, el = document) => el.querySelector(sel);
  const qsa = (sel, el = document) => Array.from(el.querySelectorAll(sel));

  function safeUrl(href) {
    try { return new URL(href, window.location.origin); }
    catch (e) { return null; }
  }

  // 1) Sidebar active highlight (server-side th:classappend 가 없을 때의 fallback)
  function markActiveNav() {
    const currentPath = window.location.pathname.replace(/\/+$/, '') || '/';
    const items = qsa('.sidebar__nav .nav__item').filter(a => a.tagName === 'A');

    if (!items.length) return;

    // clear (JS가 덮어쓰면 SSR/Thymeleaf가 만든 is-active를 깨버릴 수 있으니, 매칭이 있을 때만 교체)
    let matched = null;

    for (const a of items) {
      const url = safeUrl(a.getAttribute('href') || '');
      if (!url) continue;

      const linkPath = url.pathname.replace(/\/+$/, '') || '/';

      // exact match 우선, 그 다음 prefix match
      if (currentPath === linkPath) {
        matched = a;
        break;
      }
      if (!matched && linkPath !== '/' && currentPath.startsWith(linkPath)) {
        matched = a;
      }
    }

    if (!matched) return;

    items.forEach(a => a.classList.remove('is-active'));
    matched.classList.add('is-active');
  }

  // 2) Tabs active highlight + "현재 탭" 표시 (SSR 없이도 동작)
  function setupTabs() {
    const tabGroups = qsa('.tabs');
    if (!tabGroups.length) return;

    const params = new URLSearchParams(window.location.search);

    tabGroups.forEach(group => {
      const paramName = group.dataset.tabParam || 'tab';
      const active = params.get(paramName) || group.dataset.defaultTab || null;

      const tabs = qsa('.tab', group);
      if (!tabs.length) return;

      // active 미지정이면 첫 탭을 active로
      let activeValue = active;

      if (!activeValue) {
        const firstUrl = safeUrl(tabs[0].getAttribute('href') || '');
        activeValue = (firstUrl && firstUrl.searchParams.get(paramName)) || tabs[0].dataset.tab || '';
      }

      let activeLabel = null;

      tabs.forEach(tab => {
        const url = safeUrl(tab.getAttribute('href') || '');
        const tabValue = tab.dataset.tab || (url ? url.searchParams.get(paramName) : null);

        const isActive = (tabValue || '') === (activeValue || '');
        tab.classList.toggle('is-active', isActive);

        if (isActive) {
          activeLabel = (tab.textContent || '').trim();
        }
      });

      // indicator chip 업데이트
      const indicatorSel = group.dataset.indicator;
      if (indicatorSel) {
        const indicator = qs(indicatorSel);
        if (indicator) {
          const strong = qs('strong', indicator) || indicator;
          strong.textContent = activeLabel || '-';
        }
      }
    });
  }

  // 3) Small UX: number-only input helper (YYYYMM 등) - 선택적으로 사용
  function setupNumericInputs() {
    qsa('input[data-numeric="true"]').forEach(inp => {
      inp.addEventListener('input', () => {
        inp.value = inp.value.replace(/[^0-9]/g, '');
      });
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    markActiveNav();
    setupTabs();
    setupNumericInputs();
  });
})();
