# frozen_string_literal: true

require "json"
require "net/http"
require "uri"

module DriveTalk
  # BACKEND.md의 판 API를 부른다. DRIVE_TALK_API가 없으면 쓰지 않는다.
  module Api
    # API가 꺼져 있거나 답을 못 한다.
    class Unreachable < StandardError; end

    # API가 요청을 거절했다. 404, 409, 400, 422.
    class Refused < StandardError
      attr_reader :status

      def initialize(status, message)
        @status = status
        super(message)
      end
    end

    VERBS = { get: Net::HTTP::Get, post: Net::HTTP::Post, delete: Net::HTTP::Delete }.freeze

    def self.base = ENV["DRIVE_TALK_API"].to_s.strip.chomp("/")
    def self.on? = !base.empty?

    def self.get(path) = call(:get, path)
    def self.post(path, body = nil) = call(:post, path, body)
    def self.delete(path) = call(:delete, path)

    def self.call(verb, path, body = nil)
      uri = URI.parse("#{base}#{path}")
      req = VERBS.fetch(verb).new(uri)
      if body
        req["content-type"] = "application/json"
        req.body = JSON.generate(body)
      end
      res = Net::HTTP.start(uri.host, uri.port, use_ssl: uri.scheme == "https",
                                                open_timeout: 2, read_timeout: 5) do |http|
        http.request(req)
      end
      status = res.code.to_i
      text = res.body.to_s
      return text.empty? ? nil : JSON.parse(text) if status.between?(200, 299)
      raise Refused.new(status, reason(text)) if status.between?(400, 499)

      raise Unreachable, "판 API가 #{status}로 답했어요. (#{base})"
    rescue SystemCallError, SocketError, IOError, Timeout::Error,
           Net::ProtocolError, URI::InvalidURIError, JSON::ParserError => e
      raise Unreachable, "판 API에 연결할 수 없어요. (#{base}) #{e.class}"
    end

    def self.reason(text)
      JSON.parse(text).fetch("error", text)
    rescue JSON::ParserError, NoMethodError
      text
    end
  end
end
