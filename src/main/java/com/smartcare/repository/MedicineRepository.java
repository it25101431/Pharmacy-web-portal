package com.smartcare.repository;

import com.smartcare.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;


public interface MedicineRepository extends JpaRepository<Medicine, Long> {
    List<Medicine> findByActiveTrueOrderByNameAsc();

    @Query("select m from Medicine m where m.active = true "
         + "and (lower(m.name) like lower(concat('%', :q, '%')) or lower(coalesce(m.genericName, '')) like lower(concat('%', :q, '%'))) "
         + "and (:cat = '' or m.category = :cat) order by m.name")
    List<Medicine> search(@Param("q") String q, @Param("cat") String cat);

    @Query("select m from Medicine m where m.active = true and m.stock <= m.reorderLevel order by m.stock")
    List<Medicine> lowStock();

    List<Medicine> findByActiveTrueAndExpiryDateBeforeOrderByExpiryDateAsc(LocalDate date);

    @Query("select distinct m.category from Medicine m where m.active = true order by m.category")
    List<String> categories();
}
