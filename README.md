# config-server

这是一个spring cloud config server的改造项目。过去较为传统的做法是，在配置仓库的webHook配置上cloud bus相关的接口，然后在config server端引入bus架包和rabbitmq架包，实现配置自动刷新。</br>
本项目认为bus和rabbitmq的依赖，会造成客户端过重，并且其维护与推广变得非常困难。</br>
这里
