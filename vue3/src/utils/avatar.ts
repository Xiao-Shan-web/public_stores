/**
 * 全局统一用户头像解析
 *
 * 规则：
 * 1. 用户设置了头像（avatar 非空）→ 返回头像地址
 * 2. 未设置头像（avatar 为空/null）→ 返回空串，由 UserAvatar 组件回退默认占位
 *
 * 配合 <UserAvatar :src="resolveAvatar(user.avatar)" /> 使用：
 * - 有地址 → 显示头像图片
 * - 无地址 → 显示默认头像（SVG 占位）
 * - 图片加载失败 → 自动回退默认头像
 *
 * 空串语义：无头像，交给组件展示默认占位，杜绝空白/破图。
 */
export function resolveAvatar(avatar?: string | null): string {
  return avatar?.trim() || ''
}

export default resolveAvatar
