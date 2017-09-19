# MapleStory v113 Server

A MapleStory server eimulator which fork from [MapleStory-v113-Server-Eimulator](https://github.com/reanox/MapleStory-v113-Server-Eimulator).

## Installation

requirement

- Java 8
- Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy
- MariaDB or MySQL with created database

clone repo

```sh
git clone --recursive https://github.com/TMS-v113/server.git
```

import sql file

```sh
mysql -u root <database name> < sql/maplestory.sql
mysql -u root <database name> < sql/20_duey_packages.sql
mysql -u root <database name> < sql/50_drop_data.sql
mysql -u root <database name> < sql/53_drop_data_global.sql
```
  
copy config file

```sh
cp config.example.ini config.ini
```

set up config file

```sh
vim config.ini
```

start server

```sh
./launch.sh
```

## License

MapleStory v113 Server is licensed under [Affero General Public License (AGPL)](LICENSE).
