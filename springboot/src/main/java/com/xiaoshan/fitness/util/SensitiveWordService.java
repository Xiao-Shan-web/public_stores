package com.xiaoshan.fitness.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感内容审核（文本过滤）
 * <p>
 * 实现：词表文件 + 归一化文本匹配。
 * <ul>
 *   <li>词表：classpath:sensitive-words.txt，一行一个词，# 为注释，可运营侧直接维护；</li>
 *   <li>归一化：全角转半角、转小写、剔除空白与常见干扰符号（* · - _ ~ 等），
 *       可拦截"加 微 信""色·情"等简单变体；</li>
 *   <li>匹配：对归一化后的文本做 contains 检查（词量小，O(n*m) 足够；
 *       词表增长到数千级时可升级 AC 自动机）；</li>
 *   <li>结果：返回命中的词列表（用于日志），空列表 = 通过。</li>
 * </ul>
 * <p>
 * 局限说明：纯本地词表只能覆盖明显违规词，图片/视频内容审核
 * 与更复杂变体识别需接入云厂商内容安全 API（预留后续接入）。
 */
@Slf4j
@Component
public class SensitiveWordService {

    /** 词表资源路径 */
    private static final String WORDS_RESOURCE = "sensitive-words.txt";

    /** 归一化时剔除的干扰字符（空白、标点、常见拆字分隔符） */
    private static final String NOISE_CHARS = "[\\s\\p{Punct}\\u00B7\\u2022\\u2027\\u30FB\\uFF0C\\u3001\\u3002\\uFF1B\\uFF1A\\uFF01\\uFF1F\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\uFF5E\\u2014\\u2026\\u201C]";

    /** 敏感词集合（归一化后存储） */
    private final Set<String> words = new HashSet<>();

    @PostConstruct
    public void load() {
        List<String> loaded = new ArrayList<>();
        try (InputStream in = new ClassPathResource(WORDS_RESOURCE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                String normalized = normalize(word);
                if (!normalized.isEmpty()) {
                    words.add(normalized);
                    loaded.add(word);
                }
            }
        } catch (Exception e) {
            // 词表缺失不应阻断启动，但必须高声量告警
            log.error("敏感词表加载失败，审核服务将放行所有内容 resource={}", WORDS_RESOURCE, e);
        }
        log.info("敏感词表加载完成：{} 个词（source={}）", words.size(), WORDS_RESOURCE);
        if (log.isDebugEnabled()) {
            log.debug("敏感词样例：{}", loaded.stream().limit(5).toList());
        }
    }

    /**
     * 审核文本。
     *
     * @param text 待审核文本（标题/正文/评论）
     * @return 命中的敏感词列表（归一化前原词），空列表 = 通过
     */
    public List<String> check(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null || text.isBlank() || words.isEmpty()) {
            return hits;
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return hits;
        }
        for (String w : words) {
            if (normalized.contains(w)) {
                hits.add(w);
                if (hits.size() >= 3) {
                    // 命中足够定位即可，不穷举
                    break;
                }
            }
        }
        return hits;
    }

    /**
     * 是否通过审核
     */
    public boolean pass(String text) {
        return check(text).isEmpty();
    }

    /**
     * 归一化：全角→半角、小写、剔除空白/标点/干扰符号。
     * 词表在加载时也做同样归一化，保证两侧口径一致。
     */
    private String normalize(String input) {
        if (input == null) return "";
        char[] chars = input.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        for (char c : chars) {
            // 全角 ASCII 区（！~ 0xFF01-0xFF5E）→ 半角
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            } else if (c == 0x3000) { // 全角空格
                c = ' ';
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().replaceAll(NOISE_CHARS, "");
    }
}
