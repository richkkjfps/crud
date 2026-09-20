package com.richkkjfps.crud.repositories;

import com.richkkjfps.crud.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
}
