package com.richkkjfps.crud.commerce;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/cash")
public class CashController {
 private final CashService service;
 public CashController(CashService service){this.service=service;}
 @GetMapping("/current") public Map<String,Object> current(){return service.current();}
 @GetMapping public List<Map<String,Object>> history(){return service.history();}
 @GetMapping("/{id}") public Map<String,Object> report(@PathVariable int id){return service.report(id);}
 @PostMapping("/open") public Map<String,Object> open(@RequestBody CashService.OpenRequest request){return service.open(request);}
 @PostMapping("/{id}/close") public Map<String,Object> close(@PathVariable int id,@RequestBody CashService.CloseRequest request){return service.close(id,request);}
 @PostMapping("/movements") public Map<String,Object> move(@RequestBody CashService.MovementRequest request){return service.move(request);}
}
