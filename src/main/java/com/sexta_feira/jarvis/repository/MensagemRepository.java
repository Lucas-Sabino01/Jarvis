package com.sexta_feira.jarvis.repository;

import com.sexta_feira.jarvis.model.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MensagemRepository extends JpaRepository<Mensagem, Long> {
    // O JpaRepository já nos dá métodos como save(), findAll(), deleteById() de graça!
}