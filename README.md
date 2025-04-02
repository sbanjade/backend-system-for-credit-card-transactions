Credit Card Transaction System
A secure Java-based backend system for processing credit card transactions with robust security features including encryption, hashing, and database management.
Features

Secure Credit Card Processing: Implements industry-standard security practices
Data Encryption: AES encryption for sensitive card data
Secure Storage: Hashed card numbers with only encrypted data stored in database
Validation: Luhn algorithm verification for card numbers
Transaction Management: Complete transaction history with status tracking
Database Integration: MySQL database with proper relationship modeling

Prerequisites

Java Development Kit (JDK) 8 or higher
MySQL Database Server 5.7 or higher
MySQL Connector/J (JDBC driver)

Setup Instructions
Database Setup

Install and start MySQL server
Create a new database and user:

sqlCopyCREATE DATABASE payment_db;
CREATE USER 'payment_user'@'localhost' IDENTIFIED BY 'strong_password_here';
GRANT ALL PRIVILEGES ON payment_db.* TO 'payment_user'@'localhost';
FLUSH PRIVILEGES;
Project Setup

Clone the repository or create a new Java project
Add the MySQL JDBC driver to your project:

Maven:
xmlCopy<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.28</version>
</dependency>

Gradle:
gradleCopyimplementation 'mysql:mysql-connector-java:8.0.28'



Create the package structure: com.payment.transaction
Place the CreditCardTransactionSystem.java file in this package

Configuration
Edit the database connection parameters in the code if needed:
javaCopyprivate static final String DB_URL = "jdbc:mysql://localhost:3306/payment_db";
private static final String DB_USER = "payment_user";
private static final String DB_PASSWORD = "strong_password_here";
For production, update the encryption parameters:
javaCopyprivate static final String SECRET_KEY = "ThisIsASecretKeyForEncryption123";
private static final String INIT_VECTOR = "EncryptionInitVec";
Running the Application
From IDE

Open the project in your IDE (IntelliJ IDEA, Eclipse, or VS Code)
Run the CreditCardTransactionSystem class
