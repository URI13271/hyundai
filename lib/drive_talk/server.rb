# frozen_string_literal: true

require "cgi"
require "socket"
require "uri"

module DriveTalk
  class Server
    REASON = {
      200 => "OK", 303 => "See Other", 404 => "Not Found", 500 => "Error", 502 => "Bad Gateway"
    }.freeze

    def self.run(port)
      $stdout.sync = true
      tcp = TCPServer.new("0.0.0.0", port)
      puts "드라이브 토크  http://127.0.0.1:#{port}"
      loop { Thread.new(tcp.accept) { |sock| new(sock).process } }
    end

    def initialize(sock)
      @sock = sock
    end

    def process
      req = read_request
      return unless req
      status, headers, body = dispatch(req)
      write_response(status, headers, body)
    rescue Api::Unreachable => e
      warn e.message
      write_response(502, { "content-type" => "text/html; charset=utf-8" }, api_down(e.message))
    rescue StandardError => e
      warn e.full_message
      write_response(500, { "content-type" => "text/plain; charset=utf-8" }, e.message)
    ensure
      @sock.close
    end

    private

    def read_request
      head = +""
      while (line = @sock.gets)
        break if line == "\r\n"
        head << line
        return nil if head.bytesize > 16_384
      end
      return nil if head.empty?

      lines = head.split("\r\n")
      method, target, = lines[0].split(" ", 3)
      headers = {}
      lines[1..].each do |row|
        key, value = row.split(":", 2)
        headers[key.downcase] = value.strip if key && value
      end
      length = headers["content-length"].to_i
      body = length.positive? ? @sock.read(length).to_s : ""
      uri = URI.parse(target.to_s)
      {
        method: method, path: uri.path.empty? ? "/" : uri.path,
        query: CGI.parse(uri.query.to_s), form: CGI.parse(body),
        cookies: parse_cookie(headers["cookie"]), headers: headers
      }
    end

    def parse_cookie(raw)
      raw.to_s.split(";").each_with_object({}) do |part, jar|
        key, value = part.split("=", 2)
        jar[key.strip] = CGI.unescape(value.to_s) if key && !key.strip.empty?
      end
    end

    def param(req, key)
      found = req[:form][key]
      found = req[:query][key] if found.nil? || found.empty?
      Array(found).first
    end

    def dispatch(req)
      return asset(req[:path].delete_prefix("/assets/")) if req[:path].start_with?("/assets/")
      return [200, { "content-type" => "text/plain" }, "ok"] if req[:path] == "/health"

      theme = req[:cookies].fetch("theme", "system")
      layout = req[:cookies].fetch("layout", "auto")
      demo = req[:cookies]["demo"] == "1"
      jar = []
      if %w[light dark system].include?(param(req, "theme"))
        theme = param(req, "theme")
        jar << cookie("theme", theme)
      end
      if %w[auto landscape portrait].include?(param(req, "layout"))
        layout = param(req, "layout")
        jar << cookie("layout", layout)
      end
      if param(req, "demo") == "1"
        demo = true
        jar << cookie("demo", "1")
      end

      game = nil
      html = nil
      Store.with(req[:cookies]["dt"]) do |found|
        game = found
        found.sync(req[:cookies]["sid"]) if remote?(found)
        route(found, req) unless pref_only?(req)
        dest = SCREEN_PATH.fetch(game.screen)
        if req[:method] == "GET" && req[:path] == dest && !pref_only?(req)
          html = View.new(game, theme: theme, layout: layout, demo: demo).render
        end
      end
      jar << cookie("dt", game.id) if req[:cookies]["dt"] != game.id
      jar.concat(sid_cookie(game, req[:cookies]["sid"])) if remote?(game)
      return [200, { "content-type" => "text/html; charset=utf-8", "set-cookie" => jar }, html] if html

      redirect(SCREEN_PATH.fetch(game.screen), jar)
    end

    def remote?(game) = game.is_a?(RemoteGame)

    # 판 id는 브라우저에 둔다. 화면 서버를 다시 켜도 같은 판으로 돌아온다.
    def sid_cookie(game, have)
      return [cookie("sid", game.sid)] if game.sid && game.sid != have
      return ["sid=; Path=/; Max-Age=0; SameSite=Lax"] if game.sid.nil? && have

      []
    end

    def api_down(message)
      <<~HTML
        <!DOCTYPE html>
        <html lang="ko">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>드라이브 토크</title>
          <link rel="stylesheet" href="/assets/app.css">
          <link rel="stylesheet" href="/assets/adaptive.css">
        </head>
        <body>
          <div class="stage"><main class="device"><section class="center">
            <h2>판을 불러오지 못했어요</h2>
            <p>#{CGI.escapeHTML(message)}</p>
            <p><a class="link" href="/">다시 시도</a></p>
          </section></main></div>
        </body>
        </html>
      HTML
    end

    def pref_only?(req)
      req[:method] == "GET" && (param(req, "theme") || param(req, "layout") || param(req, "demo"))
    end

    def route(game, req)
      choice = param(req, "choice")
      seat = param(req, "id")
      case [req[:method], req[:path]]
      when ["POST", "/enter"] then game.advance_from_splash
      when ["POST", "/start"] then game.start
      when ["POST", "/mic"], ["GET", "/mic"] then game.show_mic
      when ["POST", "/mic/open"] then game.grant_mic
      when ["POST", "/home"] then game.home
      when ["POST", "/ready"]
        game.finish_shuffle if game.screen == :shuffle
        game.finish_read if game.screen == :read
      when ["POST", "/seat"] then game.toggle(seat)
      when ["POST", "/begin"] then game.begin_round
      when ["POST", "/hear"] then game.hear(choice)
      when ["POST", "/settle"] then nil
      when ["POST", "/replay"] then game.replay
      when ["POST", "/next"] then game.next_step
      when ["POST", "/leave"] then game.ask_leave
      when ["POST", "/stay"] then game.stay
      when ["POST", "/quit"] then game.quit
      when ["POST", "/again"] then game.again
      end
    end

    def asset(name)
      return missing if name.include?("..") || name.empty?

      file = File.join(ROOT, "public", name)
      return missing unless File.file?(file)

      type = { ".css" => "text/css", ".js" => "text/javascript", ".woff2" => "font/woff2" }
      [200, {
        "content-type" => type.fetch(File.extname(file), "application/octet-stream"),
        "cache-control" => "public, max-age=86400"
      }, File.binread(file)]
    end

    def missing = [404, { "content-type" => "text/plain" }, "not found"]

    def redirect(path, jar)
      [303, { "location" => path, "set-cookie" => jar }, ""]
    end

    def cookie(key, value)
      "#{key}=#{CGI.escape(value)}; Path=/; SameSite=Lax"
    end

    def write_response(status, headers, body)
      body = body.to_s.b
      out = +"HTTP/1.1 #{status} #{REASON.fetch(status, 'OK')}\r\n"
      headers.each { |key, value| Array(value).each { |item| out << "#{key}: #{item}\r\n" } }
      out << "content-length: #{body.bytesize}\r\nconnection: close\r\n\r\n"
      @sock.write(out)
      @sock.write(body)
    end
  end
end
