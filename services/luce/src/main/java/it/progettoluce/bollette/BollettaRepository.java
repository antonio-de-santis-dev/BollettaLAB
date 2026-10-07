package it.progettoluce.bollette;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BollettaRepository extends JpaRepository<BollettaConcorrente, Long> {}
