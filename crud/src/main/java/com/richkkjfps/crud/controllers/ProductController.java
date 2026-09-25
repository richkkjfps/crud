package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.dtos.ProductsDto;
import com.richkkjfps.crud.model.Product;
import com.richkkjfps.crud.repositories.ProductRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

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

    @GetMapping("/{id}")
    public ResponseEntity getById(@PathVariable(value = "id") Integer id) {

        Optional product = repository.findById(id);

        if (product.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }

        return ResponseEntity.status(HttpStatus.FOUND).body(product.get());

    }

    @PostMapping
    public ResponseEntity save(@RequestBody Product product) {

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable(value = "id") Integer id) {
        Optional<Product> product = repository.findById(id);

        if (product.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }

        repository.delete(product.get());

        return ResponseEntity.status(HttpStatus.OK).body("Deleted Product");
    }

    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable(value = "id") Integer id, @RequestBody ProductsDto dto) {
        Optional<Product> product = repository.findById(id);

        if (product.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }

        var productModel = product.get();

        BeanUtils.copyProperties(dto, productModel);

        return ResponseEntity.status(HttpStatus.OK).body(repository.save(productModel));
    }
}