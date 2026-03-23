package com.devops.studentmanagement.service;

import com.devops.studentmanagement.dto.StudentDTO;
import com.devops.studentmanagement.exception.DuplicateEmailException;
import com.devops.studentmanagement.exception.StudentNotFoundException;
import com.devops.studentmanagement.model.Student;
import com.devops.studentmanagement.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;

    // ===== GET ALL STUDENTS =====
    @Transactional(readOnly = true)
    public List<StudentDTO> getAllStudents() {
        log.info("Fetching all students");
        return studentRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ===== GET STUDENT BY ID =====
    @Transactional(readOnly = true)
    public StudentDTO getStudentById(Long id) {
        log.info("Fetching student with id: {}", id);
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
        return convertToDTO(student);
    }

    // ===== CREATE NEW STUDENT =====
    public StudentDTO createStudent(StudentDTO studentDTO) {
        log.info("Creating new student with email: {}", studentDTO.getEmail());

        // Check if email already exists
        if (studentRepository.existsByEmail(studentDTO.getEmail())) {
            throw new DuplicateEmailException(studentDTO.getEmail());
        }

        Student student = convertToEntity(studentDTO);
        Student savedStudent = studentRepository.save(student);
        log.info("Student created successfully with id: {}", savedStudent.getId());
        return convertToDTO(savedStudent);
    }

    // ===== UPDATE STUDENT =====
    public StudentDTO updateStudent(Long id, StudentDTO studentDTO) {
        log.info("Updating student with id: {}", id);

        Student existingStudent = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        // If email changed, check new email is not already taken
        if (!existingStudent.getEmail().equals(studentDTO.getEmail()) &&
                studentRepository.existsByEmail(studentDTO.getEmail())) {
            throw new DuplicateEmailException(studentDTO.getEmail());
        }

        existingStudent.setFirstName(studentDTO.getFirstName());
        existingStudent.setLastName(studentDTO.getLastName());
        existingStudent.setEmail(studentDTO.getEmail());
        existingStudent.setCourse(studentDTO.getCourse());
        existingStudent.setGrade(studentDTO.getGrade());
        existingStudent.setPhoneNumber(studentDTO.getPhoneNumber());

        Student updatedStudent = studentRepository.save(existingStudent);
        log.info("Student updated successfully with id: {}", updatedStudent.getId());
        return convertToDTO(updatedStudent);
    }

    // ===== DELETE STUDENT =====
    public void deleteStudent(Long id) {
        log.info("Deleting student with id: {}", id);
        if (!studentRepository.existsById(id)) {
            throw new StudentNotFoundException(id);
        }
        studentRepository.deleteById(id);
        log.info("Student deleted successfully with id: {}", id);
    }

    // ===== SEARCH BY NAME =====
    @Transactional(readOnly = true)
    public List<StudentDTO> searchStudentsByName(String name) {
        log.info("Searching students by name: {}", name);
        return studentRepository.searchByName(name)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ===== GET BY COURSE =====
    @Transactional(readOnly = true)
    public List<StudentDTO> getStudentsByCourse(String course) {
        log.info("Fetching students by course: {}", course);
        return studentRepository.findByCourse(course)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ===== HELPER: Convert Entity to DTO =====
    private StudentDTO convertToDTO(Student student) {
        return StudentDTO.builder()
                .id(student.getId())
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .email(student.getEmail())
                .course(student.getCourse())
                .grade(student.getGrade())
                .phoneNumber(student.getPhoneNumber())
                .createdAt(student.getCreatedAt() != null ? student.getCreatedAt().toString() : null)
                .updatedAt(student.getUpdatedAt() != null ? student.getUpdatedAt().toString() : null)
                .build();
    }

    // ===== HELPER: Convert DTO to Entity =====
    private Student convertToEntity(StudentDTO dto) {
        return Student.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .course(dto.getCourse())
                .grade(dto.getGrade())
                .phoneNumber(dto.getPhoneNumber())
                .build();
    }
}
