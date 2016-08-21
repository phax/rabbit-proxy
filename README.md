# RabbIT

Source: http://www.khelekore.org/rabbit/

RabbIT is a web proxy that speeds up web surfing over slow links by doing:

  * Compress text pages to gzip streams. This reduces size by up to 75%
  * Compress images to 10% jpeg. This reduces size by up to 95%
  * Remove advertising
  * Remove background images
  * Cache filtered pages and images
  * Uses keepalive if possible
  * Easy and powerful configuration
  * Multi threaded solution written in java
  * Modular and easily extended
  * Complete HTTP/1.1 compliance 

RabbIT is a proxy for HTTP, it is HTTP/1.1 compliant (testing being done with Co-Advisors test, http://coad.measurement-factory.com/) and should hopefully support the latest HTTP/x.x in the future. Its main goal is to speed up surfing over slow links by removing unnecessary parts (like background images) while still showing the page mostly like it is. For example, we try not to ruin the page layout completely when we remove unwanted advertising banners. The page may sometimes even look better after filtering as you get rid of pointless animated gif images.

Since filtering the pages is a "heavy" process, RabbIT caches the pages it filters but still tries to respect cache control headers and the old style "pragma: no-cache". RabbIT also accepts request for nonfiltered pages by prepending "noproxy" to the adress (like http://noproxy.www.altavista.com/). Optionally, a link to the unfiltered page can be inserted at the top of each page automatically.

RabbIT is developed and tested under Linux. Since the whole package is written in java, the basic proxy should run on any plattform that supports java. Image processing is done either by an external program or by a java based converter that runs in the rabbit jvm. The recomended program is convert (found in GraphicsMagick). RabbIT can of course be run without image processing enabled, but then you lose a lot of the time savings it gives.

RabbIT works best if it is run on a computer with a fast link (typically your ISP). Since every large image is compressed before it is sent from the ISP to you, surfing becomes much faster at the price of some decrease in image quality. If some parts of the page are already cached by the proxy, the speedup will often be quite amazing. For 1275 random images only 22% (2974108 bytes out of a total of 13402112) were sent to the client. That is 17 minutes instead of 75 using 28.8 modem.

RabbIT works by modifying the pages you visit so that your browser never sees the advertising images, it only sees one fixed image tag (that image is cached in the browser the first time it is downloaded, so sequential requests for it is made from the browsers cache, giving a nice speedup). For images RabbIT fetches the image and run it through a processor giving a low quality jpeg instead of the animated gif-image. This image is very much smaller and download of it should be quick even over a slow link (modem). 

