package com.afkgame.domain.masterdata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * マスターデータ YAML の読み込みと起動時スキーマ検証。
 *
 * <p>仕様: docs/backlog/java_migration.md §2「マスターデータ」・§5。
 * YAML は {@code afkgame-domain} の {@code src/main/resources/masterdata/} に置き、
 * 再ビルドなしで差し替えられるようにする。不正な内容は**起動時に**検出して起動を中止する
 * （実行時にマスターデータの欠損へ分岐させないため）。
 */
@Component
public class MasterDataLoader {

    /** スキーマに無いキーは既定でエラーになる（項目名の誤りを起動時に検出するため無効化しない）。 */
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final Validator validator;

    public MasterDataLoader(Validator validator) {
        this.validator = validator;
    }

    /**
     * YAML のリストを record へ読み込み、ID をキーにした不変 Map で返す。
     *
     * @param resourcePath クラスパス上の YAML（例: {@code masterdata/items.yml}）
     * @param elementType  1要素に対応する record
     * @param idExtractor  要素からキーを取り出す関数
     * @return ID をキーにした不変 Map（YAML の記載順を保つ）
     * @throws MasterDataException リソース不在・パース失敗・空・スキーマ違反・ID重複のいずれか
     */
    public <T> Map<String, T> load(String resourcePath, Class<T> elementType,
            Function<T, String> idExtractor) {
        List<T> entries = read(resourcePath, elementType);
        if (CollectionUtils.isEmpty(entries)) {
            throw new MasterDataException(resourcePath + ": マスターデータが1件も定義されていない");
        }

        Map<String, T> byId = new LinkedHashMap<>();
        for (T entry : entries) {
            validate(resourcePath, entry);
            String id = idExtractor.apply(entry);
            if (byId.putIfAbsent(id, entry) != null) {
                throw new MasterDataException(resourcePath + ": IDが重複している (" + id + ")");
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    private <T> List<T> read(String resourcePath, Class<T> elementType) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new MasterDataException(resourcePath + ": マスターデータのリソースが見つからない");
        }
        try (InputStream in = resource.getInputStream()) {
            return YAML_MAPPER.readValue(in,
                    YAML_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (IOException e) {
            throw new MasterDataException(resourcePath + ": マスターデータの読み込みに失敗", e);
        }
    }

    private <T> void validate(String resourcePath, T entry) {
        Set<ConstraintViolation<T>> violations = validator.validate(entry);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .collect(Collectors.joining(", "));
            throw new MasterDataException(resourcePath + ": スキーマ検証に失敗 (" + detail + ")");
        }
    }
}
