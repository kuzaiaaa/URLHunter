plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2025.2")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.jsoup:jsoup:1.17.2")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().filter { it.isDirectory })
    from(configurations.runtimeClasspath.get().filterNot { it.isDirectory }.map { zipTree(it) })
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)  // 强制使用 JDK 21 编译
    }
    sourceCompatibility = JavaVersion.VERSION_21  // 源码语法兼容性
    targetCompatibility = JavaVersion.VERSION_21  // 生成字节码兼容性
}
