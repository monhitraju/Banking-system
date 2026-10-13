CREATE DATABASE IF NOT EXISTS digital_banking
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE digital_banking;

CREATE TABLE IF NOT EXISTS customers (
  customer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  status ENUM('ACTIVE','LOCKED') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS accounts (
  account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  account_number VARCHAR(20) NOT NULL UNIQUE,
  balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  status ENUM('ACTIVE','FROZEN') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  CONSTRAINT chk_nonnegative_balance CHECK (balance >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transfers (
  transfer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  from_account_id BIGINT NOT NULL,
  to_account_id BIGINT NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  reference_no VARCHAR(36) NOT NULL UNIQUE,
  idempotency_key VARCHAR(36) NOT NULL UNIQUE,
  status ENUM('SUCCESS') NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_transfer_from FOREIGN KEY (from_account_id) REFERENCES accounts(account_id),
  CONSTRAINT fk_transfer_to FOREIGN KEY (to_account_id) REFERENCES accounts(account_id),
  CONSTRAINT chk_transfer_amount CHECK (amount > 0),
  CONSTRAINT chk_different_accounts CHECK (from_account_id <> to_account_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ledger_entries (
  entry_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  transfer_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  entry_type ENUM('DEBIT','CREDIT') NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  balance_after DECIMAL(15,2) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ledger_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(transfer_id),
  CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
  CONSTRAINT chk_ledger_amount CHECK (amount > 0),
  INDEX idx_ledger_account_time (account_id, created_at)
) ENGINE=InnoDB;
