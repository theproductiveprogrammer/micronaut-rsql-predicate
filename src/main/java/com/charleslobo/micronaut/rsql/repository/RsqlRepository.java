package com.charleslobo.micronaut.rsql.repository;

import java.util.List;

public interface RsqlRepository<T> {
	List<T> findByRsql(String rsql);
}
