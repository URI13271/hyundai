#!/usr/bin/env ruby
# frozen_string_literal: true

# 안드로이드 APK 둘을 만든다. Gradle 없이 SDK 도구만 쓴다.
#
#   ruby android/build.rb
#
#   android/build/drive-talk-standalone.apk  서버 없이 도는 앱
#   android/build/drive-talk-client.apk      화면 서버에 붙는 앱
#
# 필요한 것: 안드로이드 SDK의 build-tools와 platform(ANDROID_HOME), JDK 11 이상(JAVA_HOME).
# 서명은 디버그 열쇠다. 배포하려면 자기 열쇠로 다시 서명한다.

require "fileutils"
require "json"
require "rbconfig"

ROOT = File.expand_path("..", __dir__)
HERE = __dir__
OUT = File.join(HERE, "build")
VERSION = "1.0.0"
MIN_SDK = "29"
WINDOWS = RbConfig::CONFIG["host_os"].match?(/mswin|mingw|cygwin/)

def exe(path) = WINDOWS ? "#{path}.exe" : path

def newest(pattern)
  Dir.glob(pattern).max_by { |dir| Gem::Version.new(File.basename(dir)[/\d+(\.\d+)*/] || "0") }
end

def sdk_home
  local = WINDOWS ? File.join(ENV.fetch("LOCALAPPDATA", ""), "Android", "Sdk") : File.join(Dir.home, "Android", "Sdk")
  [ENV["ANDROID_HOME"], ENV["ANDROID_SDK_ROOT"], local, File.join(Dir.home, "Library", "Android", "sdk")]
    .compact.map { |dir| dir.tr("\\", "/") }.find { |dir| File.directory?(File.join(dir, "build-tools")) }
end

def java_home
  studio = ["C:/Program Files/Android/Android Studio/jbr", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"]
  ([ENV["JAVA_HOME"]] + studio).compact.map { |dir| dir.tr("\\", "/") }
                               .find { |dir| File.file?(exe(File.join(dir, "bin", "javac"))) }
end

def run(*cmd)
  return if system(*cmd)

  abort "실패: #{cmd.join(' ')}"
end

sdk = sdk_home or abort "안드로이드 SDK를 찾지 못했어요. ANDROID_HOME을 주세요."
tools = newest(File.join(sdk, "build-tools", "*")) or abort "#{sdk}/build-tools가 비었어요."
platform = newest(File.join(sdk, "platforms", "android-*")) or abort "#{sdk}/platforms가 비었어요."
jdk = java_home or abort "JDK를 찾지 못했어요. JAVA_HOME을 주세요."

android_jar = File.join(platform, "android.jar")
target_sdk = File.basename(platform)[/\d+/]
aapt2 = exe(File.join(tools, "aapt2"))
zipalign = exe(File.join(tools, "zipalign"))
java = exe(File.join(jdk, "bin", "java"))
javac = exe(File.join(jdk, "bin", "javac"))
jar = exe(File.join(jdk, "bin", "jar"))
keytool = exe(File.join(jdk, "bin", "keytool"))
d8 = [java, "-cp", File.join(tools, "lib", "d8.jar"), "com.android.tools.r8.D8"]
apksigner = [java, "-jar", File.join(tools, "lib", "apksigner.jar")]

puts "SDK #{File.basename(tools)}, #{File.basename(platform)}, JDK #{jdk}"
FileUtils.mkdir_p(OUT)

keystore = File.join(OUT, "debug.keystore")
unless File.file?(keystore)
  run(keytool, "-genkeypair", "-keystore", keystore, "-storepass", "android", "-keypass", "android",
      "-alias", "debug", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
      "-dname", "CN=Drive Talk Debug")
end

# 서버 없이 도는 앱에 넣을 화면. css와 글꼴은 public/의 것을 그대로 쓴다.
def bundle_web(dir)
  # 폴더를 두지 않는다. 윈도우의 aapt2는 하위 폴더의 파일 이름에 \ 를 넣어 앱이 찾지 못한다.
  FileUtils.cp_r(File.join(HERE, "standalone", "web", "."), dir)
  %w[app.css adaptive.css webapp.css].each do |name|
    css = File.read(File.join(ROOT, "public", name), encoding: "utf-8")
    File.write(File.join(dir, name), css.gsub('url("/assets/fonts/', 'url("'), encoding: "utf-8")
  end
  FileUtils.cp(File.join(ROOT, "public", "fonts", "PretendardVariable.woff2"), dir)
  deck = JSON.parse(File.read(File.join(ROOT, "backend", "deck.json"), encoding: "utf-8"))
  File.write(File.join(dir, "deck.js"), "window.DRIVE_TALK_DECK = #{JSON.generate(deck)};\n", encoding: "utf-8")
end

%w[standalone client].each do |flavor|
  work = File.join(OUT, flavor)
  FileUtils.rm_rf(work)
  res = File.join(work, "res", "mipmap-xxxhdpi")
  gen = File.join(work, "gen", "drivetalk")
  classes = File.join(work, "classes")
  dex = File.join(work, "dex")
  [res, gen, classes, dex].each { |dir| FileUtils.mkdir_p(dir) }

  FileUtils.cp(File.join(ROOT, "public", "icon-192.png"), File.join(res, "ic_launcher.png"))
  File.write(File.join(gen, "Config.java"), <<~JAVA)
    package drivetalk;

    final class Config {
        static final boolean STANDALONE = #{flavor == 'standalone'};
    }
  JAVA

  link = [aapt2, "link", "-o", File.join(work, "base.apk"), "-I", android_jar,
          "--manifest", File.join(HERE, flavor, "AndroidManifest.xml"),
          "--min-sdk-version", MIN_SDK, "--target-sdk-version", target_sdk,
          "--version-code", "1", "--version-name", VERSION]
  if flavor == "standalone"
    web = File.join(work, "assets")
    FileUtils.mkdir_p(web)
    bundle_web(web)
    link += ["-A", web]
  end

  run(aapt2, "compile", "--dir", File.join(work, "res"), "-o", File.join(work, "res.zip"))
  run(*link, File.join(work, "res.zip"))
  run(javac, "--release", "11", "-Xlint:-options", "-encoding", "UTF-8", "-cp", android_jar, "-d", classes,
      File.join(HERE, "src", "drivetalk", "MainActivity.java"), File.join(gen, "Config.java"))
  run(*d8, "--release", "--lib", android_jar, "--min-api", MIN_SDK, "--output", dex,
      *Dir.glob(File.join(classes, "**", "*.class")))
  run(jar, "--update", "--file", File.join(work, "base.apk"), "-C", dex, "classes.dex")

  apk = File.join(OUT, "drive-talk-#{flavor}.apk")
  run(zipalign, "-f", "-p", "4", File.join(work, "base.apk"), apk)
  run(*apksigner, "sign", "--ks", keystore, "--ks-pass", "pass:android", "--key-pass", "pass:android",
      "--ks-key-alias", "debug", apk)
  run(*apksigner, "verify", apk)
  FileUtils.rm_f("#{apk}.idsig")
  puts "#{apk}  #{(File.size(apk) / 1024.0 / 1024).round(1)} MB"
end
