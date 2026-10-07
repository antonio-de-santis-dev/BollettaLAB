package it.bollettalab.platform;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.*;

public class ScopedJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {
  final JpaEntityInformation<T, ?> info;

  public ScopedJpaRepository(JpaEntityInformation<T, ?> info, EntityManager em) {
    super(info, em);
    this.info = info;
  }

  Specification<T> scope() {
    return (root, q, cb) -> {
      Identity i = RequestContext.identity();
      if (i == null || !i.agent() || !ScopedEntity.class.isAssignableFrom(info.getJavaType()))
        return cb.conjunction();
      String type = info.getJavaType().getSimpleName();
      if (Set.of("Confronto", "BusinessSimulation").contains(type))
        return cb.equal(root.get("authorId"), i.id());
      if (type.equals("RecordEntity"))
        return cb.or(
            cb.notEqual(root.get("kind"), "confronti"), cb.equal(root.get("authorId"), i.id()));
      return cb.conjunction();
    };
  }

  @Override
  public Optional<T> findById(ID id) {
    return findOne(scope().and((r, q, c) -> c.equal(r.get(info.getIdAttribute().getName()), id)));
  }

  @Override
  public boolean existsById(ID id) {
    return findById(id).isPresent();
  }

  @Override
  public List<T> findAll() {
    return super.findAll(scope());
  }

  @Override
  public List<T> findAll(Sort sort) {
    return super.findAll(scope(), sort);
  }

  @Override
  public Page<T> findAll(Pageable page) {
    return super.findAll(scope(), page);
  }

  @Override
  public void deleteById(ID id) {
    findById(id).ifPresent(super::delete);
  }
}
