package com.charleslobo.micronaut.rsql.repository;

import com.charleslobo.micronaut.rsql.RsqlCriteriaBuilder;
import cz.jirutka.rsql.parser.RSQLParser;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;

public class AbstractRsqlRepository<T> implements RsqlRepository<T> {

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

	@Override
	public Page<T> findByRsql(String rsql, Pageable pageable) {
		CriteriaQuery<T> criteriaQuery = builder.fromRsql(rsql, entityClass);

		// Apply sorting from Pageable
		if (pageable.isSorted()) {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			Root<T> root = (Root<T>) criteriaQuery.getRoots().iterator().next();
			List<jakarta.persistence.criteria.Order> orders = pageable.getSort().getOrderBy().stream()
				.map(order -> order.isAscending()
					? cb.asc(root.get(order.getProperty()))
					: cb.desc(root.get(order.getProperty())))
				.collect(java.util.stream.Collectors.toList());
			criteriaQuery.orderBy(orders);
		}

		TypedQuery<T> query = em.createQuery(criteriaQuery);

		query.setFirstResult((int) pageable.getOffset());
		query.setMaxResults(pageable.getSize());

		List<T> results = query.getResultList();

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
		Root<T> root = countQuery.from(entityClass);
		Predicate predicate =
				builder.buildPredicate(new RSQLParser().parse(rsql), root, cb, entityClass);
		countQuery.select(cb.count(root)).where(predicate);
		Long total = em.createQuery(countQuery).getSingleResult();

		return Page.of(results, pageable, total);
	}

	@Override
	public long countByRsql(String rsql) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
		Root<T> root = countQuery.from(entityClass);

		countQuery.select(cb.count(root));

		if (rsql != null && !rsql.isBlank()) {
			Predicate predicate =
					builder.buildPredicate(
							new cz.jirutka.rsql.parser.RSQLParser().parse(rsql), root, cb, entityClass);
			countQuery.where(predicate);
		}

		return em.createQuery(countQuery).getSingleResult();
	}
}
