plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// SingleStore's recommended driver (com.singlestore.jdbc.*) — the one NOT on the
// dd-java-agent allow-list, i.e. the one we want to validate.
val singlestoreJdbcVersion = "1.2.7"
// MariaDB Connector/J version documented by SingleStore. Kept on the classpath so
// you can switch drivers via DB_URL/DB_DRIVER and compare traces side by side.
val mariadbJdbcVersion = "2.4.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")      // JdbcTemplate + HikariCP
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    runtimeOnly("com.singlestore:singlestore-jdbc-client:$singlestoreJdbcVersion")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:$mariadbJdbcVersion")
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}

// Only the executable Spring Boot jar is needed.
tasks.jar {
    enabled = false
}
