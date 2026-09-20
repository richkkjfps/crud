package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.model.Product;
import com.richkkjfps.crud.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    ProductRepository repository;

    @GetMapping
    public ResponseEntity getALL() {

        List<Product> listProducts = repository.findAll();
        return ResponseEntity.status(HttpStatus.OK).body(listProducts);
    }
    @PostMapping
    public ResponseEntity save(@RequestBody Product product) {

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(product));
    }
}
