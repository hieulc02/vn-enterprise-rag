package com.hieulc.insightragworker.validation;

import com.hieulc.insightragworker.repository.DepartmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class DepartmentValidationCache {

    private final DepartmentRepository departmentRepository;

    @SuppressWarnings("unchecked")
    private final AtomicReference<Set<String>> cacheRef = new AtomicReference<>(Collections.EMPTY_SET);

    public DepartmentValidationCache(DepartmentRepository departmentRepository){
        this.departmentRepository = departmentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init(){
        reloadCache();

        log.info("Department cache reload successfully");
    }

    public boolean isValid(String departmentName){
        return cacheRef.get().contains(departmentName);
    }

    public void reloadCache(){
        Set<String> departmentNames = departmentRepository.findAllDepartmentNames();
        cacheRef.set(Collections.unmodifiableSet(departmentNames));
    }

}
