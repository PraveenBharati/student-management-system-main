package com.devops.studentmanagement;

import com.devops.studentmanagement.dto.StudentDTO;
import com.devops.studentmanagement.exception.StudentNotFoundException;
import com.devops.studentmanagement.service.StudentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentService studentService;

    @Autowired
    private ObjectMapper objectMapper;

    private StudentDTO sampleStudent;

    @BeforeEach
    void setUp() {
        sampleStudent = StudentDTO.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .course("Computer Science")
                .grade(10)
                .phoneNumber("1234567890")
                .build();
    }

    @Test
    void shouldGetAllStudents() throws Exception {
        List<StudentDTO> students = Arrays.asList(sampleStudent);
        when(studentService.getAllStudents()).thenReturn(students);

        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].firstName").value("John"));
    }

    @Test
    void shouldGetStudentById() throws Exception {
        when(studentService.getStudentById(1L)).thenReturn(sampleStudent);

        mockMvc.perform(get("/api/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("john.doe@example.com"));
    }

    @Test
    void shouldReturnNotFoundForMissingStudent() throws Exception {
        when(studentService.getStudentById(99L))
                .thenThrow(new StudentNotFoundException(99L));

        mockMvc.perform(get("/api/students/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldCreateStudent() throws Exception {
        StudentDTO newStudent = StudentDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .course("Mathematics")
                .grade(9)
                .build();

        when(studentService.createStudent(any(StudentDTO.class))).thenReturn(sampleStudent);

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStudent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldFailValidationWhenEmailIsInvalid() throws Exception {
        StudentDTO invalidStudent = StudentDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("not-a-valid-email")
                .course("Mathematics")
                .grade(9)
                .build();

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidStudent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldDeleteStudent() throws Exception {
        doNothing().when(studentService).deleteStudent(1L);

        mockMvc.perform(delete("/api/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReturnHealthOk() throws Exception {
        mockMvc.perform(get("/api/students/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("OK"));
    }
}
