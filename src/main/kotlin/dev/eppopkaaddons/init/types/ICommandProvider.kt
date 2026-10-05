package dev.eppopkaaddons.init.types

import dev.eppopkaaddons.commands.CommandBuilder

interface ICommandProvider {
    fun CommandBuilder.command()
}