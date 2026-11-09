package dev.obrenoxs.ticketing.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final String BASE = "dev.obrenoxs.ticketing";

    private static final List<String> MODULES = List.of(
            "auth", "catalog", "sales", "payment",
            "ticketing", "notification", "platform", "observability");

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "auth", Set.of("platform", "observability"),
            "platform", Set.of("observability"),
            "observability", Set.of(),
            "catalog", Set.of("platform", "observability", "auth"),
            "sales", Set.of("catalog", "platform", "observability", "auth"),
            "payment", Set.of("sales", "ticketing", "platform", "observability", "auth"),
            "ticketing", Set.of("sales", "payment", "platform", "observability", "auth"),
            "notification", Set.of("platform", "observability", "auth"));

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    static List<String> modules() {
        return MODULES;
    }

    @ParameterizedTest
    @MethodSource("modules")
    void nobodyOutsideTheModuleTouchesItsInternalPackage(String module) {
        noClasses()
                .that().resideOutsideOfPackage(BASE + "." + module + "..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "." + module + ".internal..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @ParameterizedTest
    @MethodSource("modules")
    void moduleOnlyDependsOnAllowedModules(String module) {
        Set<String> allowed = ALLOWED.get(module);
        for (String other : MODULES) {
            if (other.equals(module) || allowed.contains(other)) {
                continue;
            }
            noClasses()
                    .that().resideInAPackage(BASE + "." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage(BASE + "." + other + "..")
                    .because(module + " must not depend on " + other)
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }
}