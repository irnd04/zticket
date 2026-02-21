@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "common", "queue :: queue-api", "seat :: seat-api" }
)
package kr.jemi.zticket.ticket;
