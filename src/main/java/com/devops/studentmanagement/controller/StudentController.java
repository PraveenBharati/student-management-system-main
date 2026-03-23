package com.devops.studentmanagement.controller;

import com.devops.studentmanagement.dto.ApiResponse;
import com.devops.studentmanagement.dto.StudentDTO;
import com.devops.studentmanagement.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StudentController {

    private final StudentService studentService;

    // GET /api/students — get all students
    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentDTO>>> getAllStudents() {
        List<StudentDTO> students = studentService.getAllStudents();
        return ResponseEntity.ok(
                ApiResponse.success("Students fetched successfully", students));
    }

    // GET /api/students/{id} — get one student by id
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentDTO>> getStudentById(@PathVariable Long id) {
        StudentDTO student = studentService.getStudentById(id);
        return ResponseEntity.ok(
                ApiResponse.success("Student fetched successfully", student));
    }

    // POST /api/students — create new student
    @PostMapping
    public ResponseEntity<ApiResponse<StudentDTO>> createStudent(
            @Valid @RequestBody StudentDTO studentDTO) {
        StudentDTO created = studentService.createStudent(studentDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Student created successfully", created));
    }

    // PUT /api/students/{id} — update existing student
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentDTO>> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentDTO studentDTO) {
        StudentDTO updated = studentService.updateStudent(id, studentDTO);
        return ResponseEntity.ok(
                ApiResponse.success("Student updated successfully", updated));
    }

    // DELETE /api/students/{id} — delete a student
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.ok(
                ApiResponse.success("Student deleted successfully", null));
    }

    // GET /api/students/search?name=john — search by name
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StudentDTO>>> searchStudents(
            @RequestParam String name) {
        List<StudentDTO> students = studentService.searchStudentsByName(name);
        return ResponseEntity.ok(
                ApiResponse.success("Search results", students));
    }

    // GET /api/students/course/{course} — get students by course
    @GetMapping("/course/{course}")
    public ResponseEntity<ApiResponse<List<StudentDTO>>> getStudentsByCourse(
            @PathVariable String course) {
        List<StudentDTO> students = studentService.getStudentsByCourse(course);
        return ResponseEntity.ok(
                ApiResponse.success("Students by course fetched successfully", students));
    }

    // GET /api/students/health — simple health check endpoint
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(
                ApiResponse.success("Student Management API is running", "OK"));
    }
}
