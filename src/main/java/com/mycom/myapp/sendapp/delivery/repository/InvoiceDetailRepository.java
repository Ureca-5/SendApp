package com.mycom.myapp.sendapp.delivery.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InvoiceDetailRepository {
	private final JdbcTemplate jdbcTemplate;
	
	public List<String> findDetails(Long invoiceId, int categoryId) {
		String sql = """
	            SELECT service_name
	            FROM monthly_invoice_detail
	            WHERE invoice_id = ?
	              AND invoice_category_id = ?
	            ORDER BY detail_id
	        """;
	        return jdbcTemplate.queryForList(sql, String.class, invoiceId, categoryId);
	    }
}
