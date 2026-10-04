package mr.xe.delid.service;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.enums.PackageType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PackageParser юнит-тесты")
public class PurlStringParserTest {

  @Test
  @DisplayName("Корректный ввод разбирается без ошибок")
  void shouldParseMavenPackageWithGroupAndVersion() {
    // given
    List<String> packages = List.of(
        "pkg:maven/com.clickhouse/clickhouse-jdbc@0.7.0",
        "pkg:pypi/pyyaml@6.0.1",
        "pkg:npm/libstdc2",
        "pkg:npm/libstdc++6@6.0.8",
        "pkg:npm/lodash",
        "pkg:npm/lodash@"
    );
    // when
    List<PurlDescriptor> actual = PurlStringParser.parsePackages(packages);
    //than
    int currentActualDescriptorPointer = -1;
    assertEquals(packages.size(), actual.size());
    PurlDescriptor currentActualDescriptor = actual.get(++currentActualDescriptorPointer);
    assertEquals(PackageType.MAVEN, currentActualDescriptor.type());
    assertEquals("clickhouse-jdbc", currentActualDescriptor.name());
    assertEquals("com.clickhouse", currentActualDescriptor.namespace());
    assertEquals("0.7.0", currentActualDescriptor.version());
    currentActualDescriptor = actual.get(++currentActualDescriptorPointer);
    assertEquals(PackageType.PYPI, currentActualDescriptor.type());
    assertEquals("pyyaml", currentActualDescriptor.name());
    assertEquals("6.0.1", currentActualDescriptor.version());
    assertNull(currentActualDescriptor.namespace());
    currentActualDescriptor = actual.get(++currentActualDescriptorPointer);
    assertEquals(PackageType.NPM, currentActualDescriptor.type());
    assertEquals("libstdc2", currentActualDescriptor.name());
    assertNull(currentActualDescriptor.namespace());
    assertNull(currentActualDescriptor.version());
    currentActualDescriptor = actual.get(++currentActualDescriptorPointer);
    assertEquals(PackageType.NPM, currentActualDescriptor.type());
    assertEquals("libstdc++6", currentActualDescriptor.name());
    assertEquals("6.0.8", currentActualDescriptor.version());
    assertNull(currentActualDescriptor.namespace());
    currentActualDescriptor = actual.get(++currentActualDescriptorPointer);
    assertEquals(PackageType.NPM, currentActualDescriptor.type());
    assertEquals("lodash", currentActualDescriptor.name());
    assertNull(currentActualDescriptor.version());
    assertNull(currentActualDescriptor.namespace());
    currentActualDescriptor = actual.get(++currentActualDescriptorPointer);
    assertEquals(PackageType.NPM, currentActualDescriptor.type());
    assertEquals("lodash", currentActualDescriptor.name());
    assertNull(currentActualDescriptor.version());
    assertNull(currentActualDescriptor.namespace());
  }


  @Test
  @DisplayName("Пустой ввод не вызывает сбоев")
  void shouldReturnEmptyListForEmptyInput() {
    List<PurlDescriptor> actual = PurlStringParser.parsePackages(Collections.emptyList());
    assertTrue(actual.isEmpty());
  }

  @Test
  @DisplayName("Некорректный идентификатор пакета игнорируется")
  void shouldSkipInvalidPackages() {
    // given
    List<String> packages = List.of(
        "pkg:maven/com.example/good@1.0",
        "invalid-package",
        "pkg:maven/com.example/good@2.0"
    );
    // when
    List<PurlDescriptor> actual = PurlStringParser.parsePackages(packages);
    // than
    assertEquals(2, actual.size());
    assertEquals("com.example", actual.get(0).namespace());
    assertEquals("com.example", actual.get(1).namespace());
  }

  @Test
  @DisplayName("Не является корректным вводом: null")
  void shouldThrowForNullInputList() {
    assertThrows(IllegalArgumentException.class, () -> PurlStringParser.parsePackages(null));
  }

  @Test
  @DisplayName("Является корректным вводом: пустой список")
  void shouldThrowForNullPackageName() {
    // given
    List<String> packages = Collections.singletonList(null);
    // when
    List<PurlDescriptor> actual = PurlStringParser.parsePackages(packages);
    // than
    assertTrue(actual.isEmpty());
  }

  @Test
  @DisplayName("Не является корректным идентификатором пакета: отсутствие префикса, слэша, имени, формата")
  void shouldSkipBadPackageIds() {
    // given
    List<String> packages = List.of(
        "maven/com.example/pkg",
        "pkg:",
        "pkg:type",
        "pkg:/name",
        "pkg:maven/",
        "pkg:maven/namespace/"
    );
    // when
    List<PurlDescriptor> actual = PurlStringParser.parsePackages(packages);
    // than
    System.out.println(actual);
    assertEquals(0, actual.size());
    assertTrue(actual.isEmpty());
  }

  @Test
  @DisplayName("Является корректным идентификатором пакета: пустое имя группы")
  void shouldThrowForEmptyNameWithGroup() {
    // given
    List<String> packages = List.of("pkg:maven//name");
    // when
    List<PurlDescriptor> actual = PurlStringParser.parsePackages(packages);
    // than
    assertEquals(1, actual.size());
    assertEquals("name", actual.get(0).name());
    assertEquals(PackageType.MAVEN, actual.get(0).type());
  }

  @Test
  @DisplayName("Является корректным идентификатором пакета: maven artefact")
  void shouldParseComplexGroupId() {
    // given
    List<String> packages = List.of("pkg:maven/org.springframework.boot/spring-boot@2.7.0");
    // when
    List<PurlDescriptor> result = PurlStringParser.parsePackages(packages);
    // than
    assertEquals(1, result.size());
    assertEquals("org.springframework.boot", result.get(0).namespace());
    assertEquals("spring-boot", result.get(0).name());
    assertEquals("2.7.0", result.get(0).version());
  }

  @Test
  @DisplayName("URL репозитория сохраняется в дескрипторе, но не попадает в purl и имя файла")
  void shouldKeepRepositoryUrlOutOfPackageIdentity() {
    // given
    String repositoryUrl = "https://repo.example.com/artifactory/api/repositories";
    // when
    PurlDescriptor result = PurlStringParser.parse("pkg:maven/org.springframework.boot/spring-boot@2.7.0",
        repositoryUrl);
    // than
    assertEquals(repositoryUrl, result.repositoryUrl());
    assertEquals("pkg:maven/org.springframework.boot/spring-boot@2.7.0", result.purl());
    assertEquals("maven_org.springframework.boot_spring-boot_2.7.0", result.getFileName());
  }

  @Test
  @DisplayName("Без URL репозитория дескриптор создаётся с null и использует репозиторий из конфигурации")
  void shouldLeaveRepositoryUrlNullWhenNotSpecified() {
    // given
    // when
    PurlDescriptor withoutUrl = PurlStringParser.parse("pkg:npm/lodash");
    PurlDescriptor withBlankUrl = PurlStringParser.parse("pkg:npm/lodash", "  ");
    // than
    assertNull(withoutUrl.repositoryUrl());
    assertEquals("lodash", withoutUrl.name());
    // пустой URL сохраняется как есть: решение «использовать репозиторий из конфигурации»
    // принимает ArtifactoryClientImpl, а не парсер
    assertEquals("  ", withBlankUrl.repositoryUrl());
    assertEquals(withoutUrl.purl(), withBlankUrl.purl());
  }
}
