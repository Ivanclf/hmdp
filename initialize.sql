UPDATE `hmdp`.`tb_seckill_voucher` SET `stock` = '100' WHERE (`voucher_id` = '10');
DELETE FROM `hmdp`.`tb_voucher_order` WHERE (`voucher_id` = '10');