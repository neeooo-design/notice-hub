CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(40) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  protected_account BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE app_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(40) NOT NULL,
  description VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE app_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(40) NOT NULL,
  module VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY(user_id,role_id),
  FOREIGN KEY(user_id) REFERENCES app_user(id),
  FOREIGN KEY(role_id) REFERENCES app_role(id)
) ENGINE=InnoDB;

CREATE TABLE role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY(role_id,permission_id),
  FOREIGN KEY(role_id) REFERENCES app_role(id),
  FOREIGN KEY(permission_id) REFERENCES app_permission(id)
) ENGINE=InnoDB;

CREATE TABLE notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL,
  category VARCHAR(20) NOT NULL,
  pinned BOOLEAN NOT NULL DEFAULT FALSE,
  created_by BIGINT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY(created_by) REFERENCES app_user(id),
  INDEX idx_notice_list(deleted,pinned,created_at),
  INDEX idx_notice_owner(created_by,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NULL,
  actor_name VARCHAR(40) NOT NULL,
  action VARCHAR(40) NOT NULL,
  target VARCHAR(120) NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  detail VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_time(created_at),
  INDEX idx_audit_action(action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_role(id,code,name,description) VALUES
(1,'EMPLOYEE','员工','阅读内部通知，维护个人资料'),
(2,'EDITOR','编辑','发布通知，维护自己创建的通知'),
(3,'ADMIN','管理员','维护全部通知，管理用户与角色，查询审计记录');

INSERT INTO app_permission(id,code,name,module) VALUES
(1,'notice:read','阅读通知','通知'),
(2,'notice:create','发布通知','通知'),
(3,'notice:update:own','修改本人通知','通知'),
(4,'notice:delete:own','删除本人通知','通知'),
(5,'notice:manage','管理全部通知','通知'),
(6,'user:read','查看用户','用户'),
(7,'user:create','创建用户','用户'),
(8,'user:update','编辑和启停用户','用户'),
(9,'role:read','查看角色权限','角色'),
(10,'role:assign','分配用户角色','角色'),
(11,'audit:read','查看审计日志','审计');

INSERT INTO role_permission(role_id,permission_id) VALUES (1,1),(2,1),(2,2),(2,3),(2,4);
INSERT INTO role_permission(role_id,permission_id) SELECT 3,id FROM app_permission;
