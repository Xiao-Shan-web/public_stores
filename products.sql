/*
SQLyog Enterprise v12.08 (64 bit)
MySQL - 5.7.17-log : Database - barbecue-payment
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`barbecue-payment` /*!40100 DEFAULT CHARACTER SET utf8 */;

USE `barbecue-payment`;

/*Table structure for table `products` */

DROP TABLE IF EXISTS `products`;

CREATE TABLE `products` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL COMMENT '商品名称',
  `description` text COMMENT '商品描述',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `original_price` decimal(10,2) NOT NULL COMMENT '商品原价',
  `image` varchar(100) DEFAULT NULL COMMENT '商品图片',
  `category` varchar(100) NOT NULL COMMENT '分类',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

/*Data for the table `products` */

insert  into `products`(`id`,`name`,`description`,`price`,`original_price`,`image`,`category`,`created_at`,`updated_at`) values (1,'烤鲍鱼','蒜蓉黄金甲，一口鲜到炸','4.00','4.00','/uploads/product_images/product_1774492537518_399.jpg','seafood','2026-03-26 10:35:38','2026-03-26 10:35:38'),(2,'烤肠','外皮焦脆爆肉汁，童年回忆香满街','3.00','3.00','/uploads/product_images/product_1774492564248_864.jpg','snack','2026-03-26 10:36:04','2026-03-26 10:36:04'),(3,'烤翅中','蜜汁渗骨三刀翅，焦香嫩滑秒光盘','6.00','6.00','/uploads/product_images/product_1774492590796_500.jpg','meat','2026-03-26 10:36:31','2026-03-26 10:36:31'),(4,'烤带鱼','银鳞炙烤椒盐洒，酥到连骨不用吐','7.00','7.00','/uploads/product_images/product_1774492611977_799.jpg','meat','2026-03-26 10:36:52','2026-03-26 10:36:52'),(5,'烤鸡翅','焦糖脆皮裹嫩肉，咬开飙汁超满足','7.00','7.00','/uploads/product_images/product_1774492633988_769.jpg','meat','2026-03-26 10:37:14','2026-03-26 10:37:14'),(6,'烤金针菇','蒜油锡纸慢火煨，鲜辣脆爽赛神仙','3.00','3.00','/uploads/product_images/product_1774492670434_932.jpg','vegetable','2026-03-26 10:37:50','2026-03-26 10:37:50'),(7,'烤韭菜','猛火锁住翡翠绿，孜然一撒香到飘','4.00','4.00','/uploads/product_images/product_1774492703225_530.jpg','vegetable','2026-03-26 10:38:23','2026-03-26 10:38:23'),(8,'烤榴莲','炭火激出奶香魂，冰火双吃会上瘾','15.00','15.00','/uploads/product_images/product_1774492735797_856.jpg','snack','2026-03-26 10:38:56','2026-03-26 10:38:56'),(9,'烤馒头','金黄脆皮炼乳淋，外酥内软像云朵','3.00','3.00','/uploads/product_images/product_1774492755440_111.jpg','staple','2026-03-26 10:39:15','2026-03-26 10:39:15'),(10,'烤年糕','鼓泡焦壳红糖脆，糯叽叽能拉丝','3.00','3.00','/uploads/product_images/product_1774492780263_250.jpg','snack','2026-03-26 10:39:40','2026-03-26 10:39:40'),(11,'烤牛筋','胶质软糯带焦边，越嚼越香粘嘴唇','2.50','2.50','/uploads/product_images/product_1774492806246_172.jpg','meat','2026-03-26 10:40:06','2026-03-26 10:40:06'),(12,'烤茄子','蒜蓉肉末铺满船，一勺挖到底才爽','7.00','7.00','/uploads/product_images/product_1774492850960_990.jpg','vegetable','2026-03-26 10:40:51','2026-03-26 10:40:51'),(13,'烤青椒','虎皮皱起爽辣汁，吃肉腻了来一口','3.00','3.00','/uploads/product_images/product_1774492896263_195.jpg','vegetable','2026-03-26 10:41:36','2026-03-26 10:41:36'),(14,'烤扇贝','粉丝吸饱海鲜汤，一口吞下整个海','3.00','3.00','/uploads/product_images/product_1774492924561_691.jpg','seafood','2026-03-26 10:42:05','2026-03-26 10:42:05'),(15,'烤苕皮','酸萝卜折耳根裹，川味暴击超解馋','5.00','5.00','/uploads/product_images/product_1774492948705_918.jpg','snack','2026-03-26 10:42:29','2026-03-26 10:42:29'),(16,'烤五花肉','三层肥瘦焦糖边，生菜一卷魂出窍','2.50','2.50','/uploads/product_images/product_1774492969641_77.jpg','meat','2026-03-26 10:42:50','2026-03-26 10:42:50'),(17,'烤虾','海盐炙出红铠甲，Q弹清甜不蘸料','4.00','4.00','/uploads/product_images/product_1774493011591_646.jpg','seafood','2026-03-26 10:43:32','2026-03-26 10:43:32'),(18,'烤鸭串','果木烟熏鸭皮脆，甜面酱香赛春饼','1.50','1.50','/uploads/product_images/product_1774493038622_573.jpg','meat','2026-03-26 10:43:59','2026-03-26 10:43:59'),(19,'烤羊腰','油脂滋啦无膻味，一口爆浆真爷们','4.00','4.00','/uploads/product_images/product_1774493113276_920.jpg','meat','2026-03-26 10:45:13','2026-03-26 10:45:13'),(20,'烤鱿鱼','铁板压出波浪纹，辣酱一刷秒回夜市','4.00','4.00','/uploads/product_images/product_1774493140168_37.jpg','seafood','2026-03-26 10:45:40','2026-03-26 10:45:40'),(21,'烤鱿鱼须','须尖焦脆须根糯，下酒追剧停不了','3.00','3.00','/uploads/product_images/product_1774493167802_768.jpg','seafood','2026-03-26 10:46:08','2026-03-26 10:46:08'),(22,'烤猪蹄','先卤后烤胶质黏，徒手啃才够江湖','15.00','15.00','/uploads/product_images/product_1774493193051_170.jpg','meat','2026-03-26 10:46:33','2026-03-26 10:46:33'),(24,'烤牛肉串','黑椒洋葱嫩到爆，汁水锁在肉缝里','2.50','2.50','/uploads/product_images/product_1774493260899_966.jpg','meat','2026-03-26 10:47:41','2026-03-26 10:47:41'),(25,'烤羊肉串','肥瘦相间羊油香，三瘦一肥新疆魂','2.00','2.00','/uploads/product_images/product_1774493285654_365.jpg','meat','2026-03-26 10:48:06','2026-03-26 10:48:06'),(26,'啤酒','冰镇泡沫冲喉爽，烧烤绝配解千愁','6.00','6.00','/uploads/product_images/product_1774493309726_810.jpg','drink','2026-03-26 10:48:30','2026-03-26 10:48:30'),(27,'生蚝','现撬蒜蓉铺满壳，鲜汤一滴不能剩','3.00','3.00','/uploads/product_images/product_1774493344437_480.jpg','seafood','2026-03-26 10:49:04','2026-03-26 10:49:04'),(28,'烤蟹排','金黄焦边蟹味浓，便宜解馋小宝藏','5.00','4.00','/uploads/product_images/product_1774493432128_607.jpg','snack','2026-03-26 10:50:32','2026-03-26 10:50:32'),(29,'一米大肉串','扛上肩的肉食者盛宴，拍照发圈必抢镜','12.00','12.00','/uploads/product_images/product_1774493465204_618.jpg','meat','2026-03-26 10:51:05','2026-03-26 10:51:05');

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
