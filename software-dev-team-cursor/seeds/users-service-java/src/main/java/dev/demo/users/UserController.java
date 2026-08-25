package dev.demo.users;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final Map<String, UserResponse> users = new ConcurrentHashMap<>();

    @PostMapping
    public UserResponse create(@RequestBody CreateUserRequest request) {
        String id = UUID.randomUUID().toString();
        String name = request == null ? "" : request.name();
        String email = request == null ? "" : request.email();
        UserResponse created = new UserResponse(id, name, email);
        users.put(id, created);
        return created;
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        UserResponse user = users.get(id);
        if (user == null) {
            return new UserResponse(id, "", "");
        }
        return user;
    }

    @GetMapping
    public List<UserResponse> list() {
        return new ArrayList<>(users.values());
    }
}
