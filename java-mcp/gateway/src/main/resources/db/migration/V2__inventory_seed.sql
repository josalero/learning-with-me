-- Iteration 3: synthetic inventory for the SQL connector demo.
create table warehouse (
    warehouse_code varchar(32) primary key,
    display_name   varchar(128) not null
);

create table product (
    product_reference varchar(32) primary key,
    product_name      varchar(128) not null
);

create table inventory (
    warehouse_code      varchar(32) not null references warehouse (warehouse_code),
    product_reference   varchar(32) not null references product (product_reference),
    quantity_available  integer not null check (quantity_available >= 0),
    primary key (warehouse_code, product_reference)
);

insert into warehouse (warehouse_code, display_name) values
    ('AUS-1', 'Austin DC'),
    ('DAL-1', 'Dallas DC');

insert into product (product_reference, product_name) values
    ('SKU-11', 'Trail runners'),
    ('SKU-22', 'Daypack 20L'),
    ('SKU-33', 'Rain shell'),
    ('SKU-44', 'Hiking poles'),
    ('SKU-55', 'Merino socks');

insert into inventory (warehouse_code, product_reference, quantity_available) values
    ('AUS-1', 'SKU-11', 40),
    ('AUS-1', 'SKU-22', 8),
    ('AUS-1', 'SKU-33', 15),
    ('AUS-1', 'SKU-44', 3),
    ('AUS-1', 'SKU-55', 2),
    ('DAL-1', 'SKU-11', 12),
    ('DAL-1', 'SKU-44', 25);
