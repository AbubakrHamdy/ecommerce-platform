-- At most one default address per user, even if two requests race.
CREATE UNIQUE INDEX uq_addresses_one_default_per_user ON addresses (user_id) WHERE is_default;
