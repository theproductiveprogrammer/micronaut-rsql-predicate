package com.charleslobo.micronaut.rsql;

import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.*;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class RsqlCriteriaBuilder {

	private final EntityManager entityManager;

	public RsqlCriteriaBuilder(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * Converts an RSQL string into a CriteriaQuery for the given entity class.
	 */
	public <T> CriteriaQuery<T> fromRsql(String rsql, Class<T> entityClass) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<T> query = cb.createQuery(entityClass);
		Root<T> root = query.from(entityClass);
		query.select(root);

		if (rsql != null && !rsql.isBlank()) {
			Node rootNode = new RSQLParser().parse(rsql);
			Predicate predicate = buildPredicate(rootNode, root, cb, entityClass);
			query.where(predicate);
		}

		return query;
	}

	/**
	 * Recursively builds a Predicate from the RSQL AST.
	 */
	private <T> Predicate buildPredicate(
			Node node, Root<T> root, CriteriaBuilder cb, Class<T> entityClass) {
		if (node instanceof AndNode) {
			List<Predicate> children =
					((AndNode) node)
							.getChildren().stream()
									.map(child -> buildPredicate(child, root, cb, entityClass))
									.collect(Collectors.toList());
			return cb.and(children.toArray(new Predicate[0]));
		} else if (node instanceof OrNode) {
			List<Predicate> children =
					((OrNode) node)
							.getChildren().stream()
									.map(child -> buildPredicate(child, root, cb, entityClass))
									.collect(Collectors.toList());
			return cb.or(children.toArray(new Predicate[0]));
		} else if (node instanceof ComparisonNode) {
			return comparisonNodeToPredicate((ComparisonNode) node, root, cb, entityClass);
		} else {
			throw new IllegalArgumentException("Unsupported RSQL node type: " + node.getClass());
		}
	}

	/**
	 * Converts a single ComparisonNode to a Predicate.
	 */
	private <T> Predicate comparisonNodeToPredicate(
			ComparisonNode comparison, Root<T> root, CriteriaBuilder cb, Class<T> entityClass) {
		String fieldName = comparison.getSelector();
		Object value =
				convertValue(comparison.getArguments().get(0), getFieldType(entityClass, fieldName));

		switch (comparison.getOperator().getSymbol()) {
			case "==":
				return cb.equal(root.get(fieldName), value);
			case "!=":
				return cb.notEqual(root.get(fieldName), value);
			case ">":
				return cb.greaterThan(root.get(fieldName), (Comparable) value);
			case "<":
				return cb.lessThan(root.get(fieldName), (Comparable) value);
			case ">=":
				return cb.greaterThanOrEqualTo(root.get(fieldName), (Comparable) value);
			case "<=":
				return cb.lessThanOrEqualTo(root.get(fieldName), (Comparable) value);
			case "=like=":
				return cb.like(root.get(fieldName), value.toString());
			default:
				throw new UnsupportedOperationException(
						"Operator not supported: " + comparison.getOperator().getSymbol());
		}
	}

	/**
	 * Returns the Java type of a given field in the entity.
	 */
	private Class<?> getFieldType(Class<?> entityClass, String fieldName) {
		try {
			Field field = entityClass.getDeclaredField(fieldName);
			return field.getType();
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("Unknown field: " + fieldName, e);
		}
	}

	/**
	 * Converts string RSQL argument to the correct Java type.
	 */
	private Object convertValue(String value, Class<?> type) {
		if (type == String.class) return value;
		else if (type == Long.class || type == long.class) return Long.valueOf(value);
		else if (type == Integer.class || type == int.class) return Integer.valueOf(value);
		else if (type == BigDecimal.class) return new BigDecimal(value);
		else if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(value);
		else if (type == Double.class || type == double.class) return Double.valueOf(value);
		else if (type == Float.class || type == float.class) return Float.valueOf(value);
		else if (type == Short.class || type == short.class) return Short.valueOf(value);
		else if (type == Byte.class || type == byte.class) return Byte.valueOf(value);
		else if (type == LocalDate.class) return LocalDate.parse(value);
		else if (type == Instant.class) return Instant.parse(value);
		else if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, value);
		else throw new IllegalArgumentException("Unsupported type: " + type);
	}
}
