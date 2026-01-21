package com.mycom.myapp.sendapp.config;

import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceProxyConfig {

    /**
     * DataSource를 감싸 쿼리 카운트를 수집할 수 있도록 proxy로 등록합니다.
     * 청크 리스너에서 QueryCountHolder로 SELECT/INSERT/UPDATE/DELETE 횟수를 읽어 로그로 남길 수 있습니다.
     */
    @Primary
    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        DataSource target = properties.initializeDataSourceBuilder().build();
        return ProxyDataSourceBuilder
                .create(target)
                .name("proxyDataSource")
                .listener(new DataSourceQueryCountListener())
                .build();
    }
}
