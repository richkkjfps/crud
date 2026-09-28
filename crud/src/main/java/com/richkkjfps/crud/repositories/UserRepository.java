package com.richkkjfps.crud.repositories;

import com.richkkjfps.crud.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository <Users, Integer> {
}
