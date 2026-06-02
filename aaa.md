基于 docs/android_dashcam_ai_implementation_plan.md，继续完成docs/tasks.md  阶段 3：热点、局域网 API 与远程查看, Task 14：实现热点控制, Task 15：实现配对 token 与基础认证
测试及环境参考 docs/env.md

完成之后git提交代码



TODO:
1. 驾驶模式加一个功能，利用加速度，gps等传感器，显示当前车速 km/h，将车速信息也记录到视频的右上角
2. 查看模式下，比如连续录制30min, 单个视频最多5min，视频被拆分为6个，时间是连续的。在查看的时候，时间连续的视频，合到一个视频上，方便拖动
3. 查看模式下，如果有紧急情况，locked视频，在进度条可以标记位置，方便用户定位