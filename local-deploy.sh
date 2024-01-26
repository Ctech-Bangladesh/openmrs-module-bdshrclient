#!/bin/sh
mvn clean package -DskipTests
scp -i ~/.ssh/id_rsa shrclient-omod/target/shrclient-2.0-SNAPSHOT.omod bahmni@192.168.33.18:~/.OpenMRS/modules
scp -i ~/.ssh/id_rsa shrclient-omod/src/main/resources/*.properties bahmni@192.168.33.18:~/.OpenMRS/
