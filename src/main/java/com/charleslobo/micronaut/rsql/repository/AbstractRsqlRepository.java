package com.charleslobo.micronaut.rsql.repository;

import com.charleslobo.micronaut.rsql.RsqlCriteriaBuilder;
import jakarta.persistence.EntityManager;
import java.util.List;

public abstract class AbstractRsqlRepository<T> implements RsqlRepository<T> {

	protected final EntityManager em;
	protected final RsqlCriteriaBuilder builder;
	protected final Class<T> entityClass;

	protected AbstractRsqlRepository(
			EntityManager em, RsqlCriteriaBuilder builder, Class<T> entityClass) {
		this.em = em;
		this.builder = builder;
		this.entityClass = entityClass;
	}

	@Override
	public List<T> findByRsql(String rsql) {
		return em.createQuery(builder.fromRsql(rsql, entityClass)).getResultList();
	}
}
