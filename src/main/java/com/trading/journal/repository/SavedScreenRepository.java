package com.trading.journal.repository;

import com.trading.journal.entity.SavedScreen;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedScreenRepository extends JpaRepository<SavedScreen, Long> {

    /** 사용자 ID로 저장된 스크린 조회 */
    @Query("SELECT s FROM SavedScreen s WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<SavedScreen> findByUserId(@Param("userId") Long userId);

    /** 사용자 ID와 이름으로 조회 */
    @Query("SELECT s FROM SavedScreen s " + "WHERE s.user.id = :userId AND s.name = :name")
    Optional<SavedScreen> findByUserIdAndName(
            @Param("userId") Long userId, @Param("name") String name);

    /** 공개된 스크린 조회 */
    @Query("SELECT s FROM SavedScreen s WHERE s.isPublic = true ORDER BY s.createdAt DESC")
    List<SavedScreen> findPublicScreens();

    /** 사용자의 공개 스크린만 조회 */
    @Query(
            "SELECT s FROM SavedScreen s "
                    + "WHERE s.user.id = :userId AND s.isPublic = true "
                    + "ORDER BY s.createdAt DESC")
    List<SavedScreen> findPublicScreensByUserId(@Param("userId") Long userId);

    /** 사용자의 스크린 개수 */
    @Query("SELECT COUNT(s) FROM SavedScreen s WHERE s.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /** 스크린 이름 존재 여부 (사용자별) */
    @Query(
            "SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END "
                    + "FROM SavedScreen s "
                    + "WHERE s.user.id = :userId AND s.name = :name")
    boolean existsByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);
}
