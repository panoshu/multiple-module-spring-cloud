CREATE TABLE IF NOT EXISTS t_bff_route_config
(
  id
  BIGINT
  PRIMARY
  KEY,
  business_type
  VARCHAR
(
  64
) NOT NULL,
  service_name VARCHAR
(
  128
) NOT NULL,
  channel_scope VARCHAR
(
  32
) NOT NULL DEFAULT 'ALL',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  description VARCHAR
(
  256
),
  created_by VARCHAR
(
  64
),
  create_time TIMESTAMP,
  updated_by VARCHAR
(
  64
),
  update_time TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  version INT NOT NULL DEFAULT 0
  );

CREATE UNIQUE INDEX IF NOT EXISTS uk_bff_route_business_channel
  ON t_bff_route_config(business_type, channel_scope, deleted);
