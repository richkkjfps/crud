package com.richkkjfps.crud.controllers;

import com.richkkjfps.crud.dtos.UserDto;
import com.richkkjfps.crud.model.Product;
import com.richkkjfps.crud.model.Users;
import com.richkkjfps.crud.repositories.UserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    UserRepository repository;

    @GetMapping
    public ResponseEntity getAll() {

        List<Users> listUsers = repository.findAll();
        return ResponseEntity.status(HttpStatus.OK).body(listUsers);
    }

    @GetMapping("/{id}")
    public ResponseEntity getById(@PathVariable(value = "Id") Integer id) {

        Optional users = repository.findById(id);

        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        return ResponseEntity.status(HttpStatus.FOUND).body(users.get());
    }

    @PostMapping
    public ResponseEntity save(@RequestBody Users users) {

        return ResponseEntity.status(HttpStatus.CREATED).body("User created");

    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable(value = "Id") Integer id) {
        Optional<Users> users = repository.findById(id);

        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        repository.delete(users.get());
        return ResponseEntity.status(HttpStatus.OK).body("User deleted");
    }

    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable(value = "Id") Integer id, @RequestBody UserRepository dto) {
        Optional<Users> users = repository.findById(id);

        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        var usersModel = users.get();

        BeanUtils.copyProperties(dto, usersModel);
        return ResponseEntity.status(HttpStatus.OK).body(repository.save(usersModel));
    }
}
