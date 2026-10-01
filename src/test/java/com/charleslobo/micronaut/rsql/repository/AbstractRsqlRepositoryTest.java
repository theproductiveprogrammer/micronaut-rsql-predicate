package com.charleslobo.micronaut.rsql.repository;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.charleslobo.micronaut.rsql.RsqlCriteriaBuilder;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class AbstractRsqlRepositoryTest {

    public static class Thing {
        private String name;
    }

    private EntityManager em;
    private CriteriaBuilder cb;
    private CriteriaQuery<Thing> listQuery;
    private CriteriaQuery<Long> countQuery;
    private AbstractRsqlRepository<Thing> repo;

    @Before
    public void setUp() {
        em = mock(EntityManager.class);
        cb = mock(CriteriaBuilder.class);
        listQuery = mock(CriteriaQuery.class);
        countQuery = mock(CriteriaQuery.class);
        when(em.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Thing.class)).thenReturn(listQuery);
        when(cb.createQuery(Long.class)).thenReturn(countQuery);
        when(listQuery.from(Thing.class)).thenReturn(mock(Root.class));
        when(countQuery.from(Thing.class)).thenReturn(mock(Root.class));
        when(countQuery.select(any())).thenReturn(countQuery);

        TypedQuery<Thing> typed = mock(TypedQuery.class);
        when(typed.getResultList()).thenReturn(Collections.emptyList());
        when(em.createQuery(listQuery)).thenReturn(typed);
        TypedQuery<Long> typedCount = mock(TypedQuery.class);
        when(typedCount.getSingleResult()).thenReturn(0L);
        when(em.createQuery(countQuery)).thenReturn(typedCount);

        repo = new AbstractRsqlRepository<>(em, new RsqlCriteriaBuilder(em), Thing.class) {};
    }

    @Test
    public void pagedFindWithBlankRsqlSkipsWhereInCountQuery() {
        for (String rsql : new String[] {"", "   ", null}) {
            Page<Thing> page = repo.findByRsql(rsql, Pageable.from(0, 10));
            assertEquals(0L, page.getTotalSize());
        }
        verify(countQuery, never()).where(any(jakarta.persistence.criteria.Expression.class));
        verify(countQuery, never()).where(any(jakarta.persistence.criteria.Predicate[].class));
    }
}
