# RAG demo with Spring AI, OpenAI and Oracle 23ai
This repository contains the source code for the [RAG made easy with Spring AI + Elasticsearch](https://www.elastic.co/search-labs/blog/java-rag-spring-ai-es) blog post.

# entro nel pod

docker exec -it oracle_23ai_latest /bin/bash

#connetto come sysdba
sqlplus sys/<LaTuaPassword>@FREEPDB1 as sysdba

#concedo i permessi
ALTER USER PDBADMIN ACCOUNT UNLOCK;
GRANT CREATE TABLE TO PDBADMIN;
GRANT UNLIMITED TABLESPACE TO PDBADMIN;
COMMIT;
EXIT;



<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="maven.apache.org"
xmlns:xsi="www.w3.org"
xsi:schemaLocation="maven.apache.org maven.apache.org">
<modelVersion>4.0.0</modelVersion>

    <!-- 1. PARENT SPRING BOOT STARTER -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.1</version> <!-- Usa una versione stabile e recente di Spring Boot 3 -->
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <groupId>com.example</groupId>
    <artifactId>oracle-ai-rag</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>oracle-ai-rag</name>
    <description>Demo project for Spring Boot and Oracle AI Vector Search</description>

    <properties>
        <java.version>17</java.version>
        <!-- Definisci la versione di Spring AI in un'unica posizione -->
        <spring-ai.version>1.1.0-M4</spring-ai.version> 
    </properties>

    <!-- 2. GESTIONE DIPENDENZE (BOM) DI SPRING AI -->
    <!-- Questa sezione importa il "Bill of Materials" per allineare le versioni AI -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- 3. DIPENDENZE DEL PROGETTO -->
    <dependencies>
        <!-- Spring Boot Standard -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        
        <!-- Dipendenze Spring AI (senza specificare la <version> qui) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        </dependency>
        <!-- Dipendenza per il Vector Store Oracle nativo (senza specificare la <version> qui) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-vector-store-oracle</artifactId>
        </dependency>

        <!-- Driver Oracle JDBC -->
        <dependency>
            <groupId>com.oracle.database.jdbc</groupId>
            <artifactId>ojdbc11</artifactId> <!-- Usa ojdbc8 se usi Java 8/11 -->
            <scope>runtime</scope>
        </dependency>

        <!-- Dipendenze di Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- 4. REPOSITORY AGGIUNTIVI (NECESSARI PER LE VERSIONI MILESTONE) -->
    <!-- Spring AI 1.1.0-M4 non è su Maven Central, è qui: -->
    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>repo.spring.io</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>

    <!-- 5. BUILD CONFIGURATION -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>