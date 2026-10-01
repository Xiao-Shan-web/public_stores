package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.FaceFeature;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * 人脸特征 Mapper
 * 每用户最多一条（uk_face_features_user_id 唯一约束）
 * registerFace：已有则更新 face_token + image_hash，否则插入
 */
@Mapper
public interface FaceFeatureMapper {

    /** 查询列片段（含 image_hash，供各 SELECT 复用） */
    String COLUMNS = "id, user_id AS userId, face_token AS faceToken, image_hash AS imageHash, " +
            "created_at AS createdAt, updated_at AS updatedAt";

    /**
     * 根据用户ID查询人脸特征
     */
    @Select("SELECT " + COLUMNS + " FROM face_features WHERE user_id = #{userId}")
    Optional<FaceFeature> findByUserId(@Param("userId") Long userId);

    /**
     * 根据 face_token 查询人脸特征
     */
    @Select("SELECT " + COLUMNS + " FROM face_features WHERE face_token = #{faceToken}")
    Optional<FaceFeature> findByFaceToken(@Param("faceToken") String faceToken);

    /**
     * 查询全部已录入人脸特征（刷脸核销时遍历比对；只返回有 image_hash 的记录）
     */
    @Select("SELECT " + COLUMNS + " FROM face_features WHERE image_hash IS NOT NULL")
    List<FaceFeature> findAll();

    /**
     * 插入人脸特征（首次录入）
     */
    @Insert("INSERT INTO face_features(id, user_id, face_token, image_hash) " +
            "VALUES(#{id}, #{userId}, #{faceToken}, #{imageHash})")
    int insert(FaceFeature faceFeature);

    /**
     * 更新 face_token 与 image_hash（重新录入时覆盖）
     */
    @Update("UPDATE face_features SET face_token = #{faceToken}, image_hash = #{imageHash} " +
            "WHERE user_id = #{userId}")
    int updateFaceToken(@Param("userId") Long userId,
                        @Param("faceToken") String faceToken,
                        @Param("imageHash") Long imageHash);

    /**
     * 删除用户的人脸登记（注销人脸）
     * <p>
     * 注意：face_features 只是「系统登记」，识别底库另存于百度人脸组 / SQLite 向量库，
     * 注销时必须由 FaceService.deleteFace 一并清理，否则底库会残留可匹配的人脸。
     *
     * @return 删除行数（0 表示本就未登记，可幂等处理）
     */
    @Delete("DELETE FROM face_features WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

}
