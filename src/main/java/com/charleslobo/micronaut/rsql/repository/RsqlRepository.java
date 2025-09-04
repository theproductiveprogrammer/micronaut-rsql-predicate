package com.charleslobo.micronaut.rsql.repository;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import java.util.List;

public interface RsqlRepository<T> {
	List<T> findByRsql(String rsql);

	Page<T> findByRsql(String rsql, Pageable pageable);
}
