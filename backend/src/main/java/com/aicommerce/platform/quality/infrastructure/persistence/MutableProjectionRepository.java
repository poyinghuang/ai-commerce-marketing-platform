package com.aicommerce.platform.quality.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface MutableProjectionRepository<T, ID> extends Repository<T, ID> {
    <S extends T> S save(S entity);
    <S extends T> S saveAndFlush(S entity);
    Optional<T> findById(ID id);
}
