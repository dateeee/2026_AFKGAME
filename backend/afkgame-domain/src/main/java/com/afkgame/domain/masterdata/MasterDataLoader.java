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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.dataformat.yaml.YAMLMapper;

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

    /**
     * スキーマに無いキーはエラーにする（項目名の誤りを起動時に検出するため）。
     *
     * <p>Jackson 3 の {@code FAIL_ON_UNKNOWN_PROPERTIES} は既定が無効のため明示的に有効化する
     * （Jackson 2 は既定で有効だった。移行 STEP 2R-C・2R-D）。
     */
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

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

    /**
     * IDキーを持たない単一オブジェクトの YAML を record へ読み込む。
     *
     * <p>ネストした record も検証するため、対象 record のフィールドには {@code @Valid} を付ける
     * （{@link InitialPlayerData} を参照）。
     *
     * @param resourcePath クラスパス上の YAML（例: {@code masterdata/initial_player.yml}）
     * @param type         対応する record
     * @return 読み込んだ record
     * @throws MasterDataException リソース不在・パース失敗・スキーマ違反のいずれか
     */
    public <T> T loadSingle(String resourcePath, Class<T> type) {
        T entry = read(resourcePath, YAML_MAPPER.getTypeFactory().constructType(type));
        validate(resourcePath, entry);
        return entry;
    }

    private <T> List<T> read(String resourcePath, Class<T> elementType) {
        return read(resourcePath,
                YAML_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    private <T> T read(String resourcePath, JavaType targetType) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new MasterDataException(resourcePath + ": マスターデータのリソースが見つからない");
        }
        try (InputStream in = resource.getInputStream()) {
            return YAML_MAPPER.readValue(in, targetType);
        } catch (IOException | JacksonException e) {
            // Jackson 3 のパース・マッピング失敗は非検査例外（JacksonException）で飛ぶ
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
