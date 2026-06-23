package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @Query("SELECT DISTINCT d.name FROM Department d")
    Set<String> findAllDepartmentNames();

}
