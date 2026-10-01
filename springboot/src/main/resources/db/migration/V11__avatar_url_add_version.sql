-- ============================================================
-- V11: 历史头像 URL 统一追加 ?v= 版本号
-- uploadAvatar 新上传的头像 URL 已带 ?v=时间戳（见 ProfileController），
-- 此迁移给历史数据补齐版本号，保证所有接口返回的头像地址格式一致。
-- 换头像后 URL 变化（新文件名 + 新版本号），浏览器自然刷新，不命中旧缓存。
-- ============================================================

UPDATE `user_profiles`
SET `avatar` = CONCAT(`avatar`, '?v=1')
WHERE `avatar` IS NOT NULL
  AND `avatar` <> ''
  AND `avatar` NOT LIKE '%?v=%';

UPDATE `user_profiles`
SET `avatar_thumb` = CONCAT(`avatar_thumb`, '?v=1')
WHERE `avatar_thumb` IS NOT NULL
  AND `avatar_thumb` <> ''
  AND `avatar_thumb` NOT LIKE '%?v=%';
