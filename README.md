# config-server

这是一个spring cloud config server的改造项目。过去较为传统的做法是，在配置仓库的webHook配置上cloud bus相关的接口，然后在config server端引入bus架包和rabbitmq架包，实现配置自动刷新。</br>
本项目认为bus和rabbitmq的依赖，会造成客户端过重，并且其维护与推广变得非常困难。</br>
这里首先在使用了最基本config server功能的基础上，利用jgit实现了对于git获取、版本分析的操作，通过周期较短的定时任务达到伪实时刷新配置的目的。</br>
另外，使用了更轻量的redis来实现最基础的mq功能，每当定时任务检测分析到配置更新时，将立即以消息的形式把最新配置发送到客户端去。
