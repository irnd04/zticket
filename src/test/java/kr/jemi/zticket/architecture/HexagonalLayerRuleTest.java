package kr.jemi.zticket.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "kr.jemi.zticket", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalLayerRuleTest {

    @ArchTest
    static final ArchRule domain은_아무_의존도_없다 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..application..", "..infrastructure..", "..api.."
            );

    @ArchTest
    static final ArchRule port_in은_domain에게만_의존한다 =
        noClasses()
            .that().resideInAPackage("..application.port.in..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..application.port.out..", "..application.service..", "..infrastructure..", "..api.."
            );

    @ArchTest
    static final ArchRule port_out은_domain에게만_의존한다 =
        noClasses()
            .that().resideInAPackage("..application.port.out..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..application.port.in..", "..application.service..", "..infrastructure..", "..api.."
            );

    @ArchTest
    static final ArchRule application_service는_domain과_port와_api에만_의존한다 =
        noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..infrastructure.."
            );

    @ArchTest
    static final ArchRule infrastructure_in은_domain과_port_in에만_의존한다 =
        noClasses()
            .that().resideInAPackage("..infrastructure.in..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..application.port.out..", "..application.service..", "..infrastructure.out..", "..api.."
            );

    @ArchTest
    static final ArchRule infrastructure_out은_domain과_port_out과_api에만_의존한다 =
        noClasses()
            .that().resideInAPackage("..infrastructure.out..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..application.port.in..", "..application.service..", "..infrastructure.in.."
            );

    // application은 다른 모듈의 api에 직접 의존하면 안 된다 (port out → infrastructure out을 통해 접근)
    @ArchTest
    static final ArchRule queue_application은_외부_모듈_api에_직접_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..queue.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..ticket.api..", "..seat.api..");

    @ArchTest
    static final ArchRule ticket_application은_외부_모듈_api에_직접_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..ticket.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..queue.api..", "..seat.api..");

    @ArchTest
    static final ArchRule seat_application은_외부_모듈_api에_직접_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..seat.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..queue.api..", "..ticket.api..");
}
